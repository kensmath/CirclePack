function [w] = ApplyMob(M,z);
% [w] = ApplyMob(M,z) Apply Mobius to z

Z=[z;1];
W=M*Z;
w=W(1)/W(2);

end